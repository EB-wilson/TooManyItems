package tmi.util

import arc.Core
import arc.files.Fi
import arc.struct.ObjectMap
import tmi.TooManyItems
import java.util.*

object TmiAssets {
  private val docCache: ObjectMap<Fi, String> = ObjectMap()

  fun getInternalFile(path: String): Fi = TooManyItems.modFile.child(path)

  fun getDocument(name: String, cache: Boolean = true): String {
    val fi = getDocumentFile(name)
    return if (cache) docCache.get(fi) { fi.readString() } else fi.readString()
  }
  fun getDocument(name: String, locale: Locale): String = getDocumentFile(name, locale).readString()

  fun getDocumentFile(name: String, locale: Locale = Core.bundle.locale): Fi {
    var docs = getInternalFile("documents").child(locale.toString())
    if (!docs.exists()) docs = getInternalFile("documents").child("en")
    return docs.child(name).also {
      if(!it.exists()) throw NoSuchFileException(it.file(), reason = "Cannot find this file in documents directory.")
    }
  }
}