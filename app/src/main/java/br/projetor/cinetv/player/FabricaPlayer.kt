package br.projetor.cinetv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.BandwidthMeter
import okhttp3.OkHttpClient

/**
 * ExoPlayer ajustado ao projetor: o mesmo OkHttpClient da API (User-Agent aceito pelo Cloudflare
 * e redirecionamentos para a CDN) e buffer limitado, porque o heap do app e de 128 MB
 * e o padrao do Media3 chega a ~130 MB em fluxo muxado.
 */
@OptIn(UnstableApi::class)
object FabricaPlayer {

    fun aoVivo(contexto: Context, http: OkHttpClient, medidor: BandwidthMeter): ExoPlayer = criar(
        contexto, http, medidor,
        minimoMs = 4_000, maximoMs = 12_000, paraTocarMs = 1_000, aposTravarMs = 2_000,
        limiteBytes = 12 * 1024 * 1024,
    )

    fun sobDemanda(contexto: Context, http: OkHttpClient, medidor: BandwidthMeter): ExoPlayer = criar(
        contexto, http, medidor,
        minimoMs = 10_000, maximoMs = 20_000, paraTocarMs = 1_500, aposTravarMs = 3_000,
        limiteBytes = 16 * 1024 * 1024,
    )

    /** O medidor de banda e o da aplicacao: mede o download real e alimenta a qualidade automatica. */
    private fun criar(
        contexto: Context,
        http: OkHttpClient,
        medidor: BandwidthMeter,
        minimoMs: Int,
        maximoMs: Int,
        paraTocarMs: Int,
        aposTravarMs: Int,
        limiteBytes: Int,
    ): ExoPlayer {
        val buffer = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minimoMs, maximoMs, paraTocarMs, aposTravarMs)
            .setTargetBufferBytes(limiteBytes)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        return ExoPlayer.Builder(contexto)
            .setMediaSourceFactory(DefaultMediaSourceFactory(contexto).setDataSourceFactory(OkHttpDataSource.Factory(http)))
            .setLoadControl(buffer)
            .setBandwidthMeter(medidor)
            .build()
    }
}
