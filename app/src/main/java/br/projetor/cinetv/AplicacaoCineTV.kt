package br.projetor.cinetv

import android.app.Application
import androidx.annotation.OptIn
import br.projetor.cinetv.dados.TesteVelocidade
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import br.projetor.cinetv.dados.PreferenciaQualidade
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import br.projetor.cinetv.dados.ArmazenamentoCredenciais
import br.projetor.cinetv.dados.Registro
import br.projetor.cinetv.dados.RepositorioGuia
import br.projetor.cinetv.dados.banco.BancoCineTV
import br.projetor.cinetv.dados.usuario.BancoUsuario
import br.projetor.cinetv.dados.xtream.ClienteXtream
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Dependencias unicas do app. Um so OkHttpClient serve a API, o player e as imagens. */
class AplicacaoCineTV : Application(), SingletonImageLoader.Factory {

    val http: OkHttpClient by lazy {
        // O User-Agent padrao do OkHttp passa pelo Cloudflare do servidor (o do curl e bloqueado)
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    val banco: BancoCineTV by lazy { BancoCineTV.criar(this) }

    val usuario: BancoUsuario by lazy { BancoUsuario.criar(this) }

    val credenciais by lazy { ArmazenamentoCredenciais(this) }

    fun cliente(): ClienteXtream? = credenciais.ler()?.let { ClienteXtream(http, it) }

    val guia: RepositorioGuia by lazy { RepositorioGuia(::cliente) }

    private val preferencias by lazy { getSharedPreferences("qualidade", MODE_PRIVATE) }

    /** Padrao geral de qualidade dos canais (Ajustes). */
    var preferenciaQualidade: PreferenciaQualidade
        get() = runCatching { PreferenciaQualidade.valueOf(preferencias.getString(CHAVE_PREFERENCIA, null)!!) }
            .getOrDefault(PreferenciaQualidade.AUTOMATICA)
        set(valor) = preferencias.edit().putString(CHAVE_PREFERENCIA, valor.name).apply()

    /** Ultima velocidade medida pelo TesteVelocidade, guardada entre aberturas do app. */
    val estimativaBanda: Long?
        get() = preferencias.getLong(CHAVE_BANDA, 0L).takeIf { it > 0 }

    /** Medidor de banda unico dos players (uso interno do Media3; nao alimenta a qualidade automatica). */
    @OptIn(UnstableApi::class)
    val medidorBanda: DefaultBandwidthMeter by lazy { DefaultBandwidthMeter.Builder(this).build() }

    /** Mede a internet se a ultima medicao tiver mais de 6 h. Roda em segundo plano ao abrir o app. */
    suspend fun medirVelocidadeSePreciso(forcar: Boolean = false) {
        val ultima = preferencias.getLong(CHAVE_BANDA_EM, 0L)
        if (!forcar && System.currentTimeMillis() - ultima < VALIDADE_MEDICAO_MS) return
        val cliente = cliente() ?: return
        val medida = TesteVelocidade(http, banco.catalogo()).medir(cliente) ?: return
        preferencias.edit().putLong(CHAVE_BANDA, medida).putLong(CHAVE_BANDA_EM, System.currentTimeMillis()).apply()
        Registro.info("Teste de velocidade: ${medida / 1000} kbps")
    }

    override fun onCreate() {
        super.onCreate()
        credenciais.ler()?.let { Registro.registrarSegredos(it.usuario, it.senha) }
    }

    private companion object {
        const val CHAVE_PREFERENCIA = "preferencia"
        const val CHAVE_BANDA = "banda_bps"
        const val CHAVE_BANDA_EM = "banda_medida_em"
        const val VALIDADE_MEDICAO_MS = 6L * 60 * 60 * 1000
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            // Heap de 128 MB dividido com o player: cache pequeno de memoria
            .memoryCache { MemoryCache.Builder().maxSizeBytes(20L * 1024 * 1024).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("imagens"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .build()
}
