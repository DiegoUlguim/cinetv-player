package br.projetor.cinetv.dados.xtream

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeToSequence
import java.io.InputStream

/**
 * Leitura tolerante do JSON do Xtream. Os paineis variam o tipo de um item para outro
 * (texto vazio no lugar de numero, inteiro ou decimal, null), entao nada aqui usa modelo estrito:
 * cada item vira JsonObject e os campos sao lidos com os conversores abaixo.
 */
object LeitorJson {

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Percorre um array JSON grande sem carregar tudo; item que nao for objeto e contado como ignorado. */
    @OptIn(ExperimentalSerializationApi::class)
    fun percorrerArray(entrada: InputStream, aoItem: (JsonObject) -> Unit): Int {
        var ignorados = 0
        json.decodeToSequence<JsonElement>(entrada, DecodeSequenceMode.ARRAY_WRAPPED).forEach { elemento ->
            if (elemento is JsonObject) aoItem(elemento) else ignorados++
        }
        return ignorados
    }
}

/** Texto do campo, ou null se ausente, null ou vazio. Numero vira texto. */
fun JsonObject.texto(chave: String): String? {
    val valor = this[chave] ?: return null
    if (valor is JsonNull || valor !is JsonPrimitive) return null
    val conteudo = valor.content.trim()
    return conteudo.ifEmpty { null }
}

/** Inteiro do campo, aceitando numero ou texto numerico ("12", "12.0"). */
fun JsonObject.inteiro(chave: String): Int? {
    val conteudo = texto(chave) ?: return null
    return conteudo.toIntOrNull() ?: conteudo.toDoubleOrNull()?.toInt()
}

/** Inteiro longo (datas em epoch do Xtream). */
fun JsonObject.longo(chave: String): Long? {
    val conteudo = texto(chave) ?: return null
    return conteudo.toLongOrNull() ?: conteudo.toDoubleOrNull()?.toLong()
}

/** Decimal do campo, aceitando inteiro, decimal ou texto. */
fun JsonObject.decimal(chave: String): Double? = texto(chave)?.replace(',', '.')?.toDoubleOrNull()

/** Primeiro texto de um campo que pode vir como lista ou como texto (ex.: backdrop_path). */
fun JsonObject.primeiroTexto(chave: String): String? {
    return when (val valor = this[chave]) {
        is JsonArray -> valor.firstOrNull { it is JsonPrimitive && it.content.isNotBlank() }?.let { (it as JsonPrimitive).content }
        is JsonPrimitive -> texto(chave)
        else -> null
    }
}
