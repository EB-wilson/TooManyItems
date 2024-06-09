package tmi

import arc.Core
import arc.func.Cons
import arc.graphics.g2d.TextureRegion
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.Log
import arc.util.serialization.Jval
import mindustry.Vars
import mindustry.mod.Mods.LoadedMod
import mindustry.world.Block
import org.intellij.lang.annotations.Language
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemStack
import tmi.recipe.RecipeType
import tmi.recipe.types.RecipeItem

class ModAPI {
  companion object {
    private val recipeNameMap = ObjectMap<String, RecipeType>()

    @Language("Nashorn JS")
    private const val initJS =
      """
      "use strict";
      
      const afterInits = []
      
      function afterInit(func){ afterInits.push(func) }
      
      const formatter = (method) => new AmountFormatter(){ format: method }
      
      const TMI = Packages.rhino.NativeJavaPackage("tmi", Vars.mods.mainLoader())
      Packages.rhino.ScriptRuntime.setObjectProtoAndParent(TMI, Vars.mods.scripts.scope)
      
      importPackage(TMI)
      importPackage(TMI.recipe)
      importPackage(TMI.recipe.parser)
      importPackage(TMI.recipe.types)
      importPackage(TMI.ui)
      importPackage(TMI.designer)
      importPackage(TMI.util)
      
      const AmountFormatter = Packages.tmi.recipe.RecipeItemStack.AmountFormatter
      """

    @Language("Nashorn JS")
    private const val afterInit =
      """
      afterInits.forEach(f => f())
      """

    @Suppress("UNCHECKED_CAST")
    fun <T: RecipeType> getRecipeType(name: String): T = recipeNameMap[name] as T

    fun setRecipeTypeName(name: String, type: RecipeType) {
      recipeNameMap[name] = type
    }

    init {
      recipeNameMap.putAll(
        "factory" to RecipeType.factory,
        "building" to RecipeType.building,
        "collecting" to RecipeType.collecting,
        "generator" to RecipeType.generator,
      )
    }
  }

  private val entries = Seq<RecipeEntry>()

  fun init() {
    //declare tmi script APIs
    Vars.mods.scripts.context.evaluateString(
      Vars.mods.scripts.scope,
      initJS,
      "tmiGlobal.js",
      0
    )

    for (mod in Vars.mods.list()) {
      if (!mod.enabled()) continue

      val modMeta = if (mod.root.child("mod.json").exists()) mod.root.child("mod.json")
      else if (mod.root.child("mod.hjson").exists()) mod.root.child("mod.hjson")
      else if (mod.root.child("plugin.json").exists()) mod.root.child("plugin.json") else mod.root.child("plugin.hjson")

      if (!modMeta.exists()) return

      readJsonAPI(mod)
      loadModJavaEntries(mod, Jval.read(modMeta.readString()))
      loadModScriptEntries(mod, Jval.read(modMeta.readString()))
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun loadModJavaEntries(mod: LoadedMod, meta: Jval) {
    val entryAnno = mod.main?.let { it::class.java.getAnnotation(RecipeEntryPoint::class.java) }
    if (!meta.has("recipeEntry") && entryAnno == null) return

    val entryClass = entryAnno?.value?.java
      ?:mod.loader.loadClass(meta.getString("recipeEntry")) as Class<out RecipeEntry>

    try {
      if (mod.loader == null) mod.loader = Vars.platform.loadJar(mod.file, Vars.mods.mainLoader())
      val entry = entryClass.getConstructor().newInstance()
      entries.add(entry)

      entry.init()
    } catch (e: Throwable) {
      Log.err("load recipe entry error, mod: ${mod.name}.", e)
    }
  }

  private fun loadModScriptEntries(mod: LoadedMod, meta: Jval) {
    if (!meta.has("recipeScript")) return

    val entryPath = meta.getString("recipeScript")
    val fi = mod.root.child(entryPath)

    if (fi.exists()){
      try {
        Vars.mods.scripts.run(mod, fi)
      } catch(t: Throwable){
        Log.err("error in recipe script: $fi, mod: ${mod.name}", t);
      }
    }
    else {
      Log.err("load recipe entry error, mod: ${mod.name}.\ndeclared recipe script is not existed.")
    }
  }

  /* 从JSON解析配方列表，灵活性极差，格式如下：
  {
    recipeItems: [
      {
        name: "$name",
        localizeNamePath: "$bundle_name", // default: "name.$name"
        icon: "$atlasName",
        ordinal: "#ordinal", // default: -1
        typeID: "#typeID", // default: -1
        hidden: boolean // default: false
      },
      ...
    ],

    recipeList: [
      {
        type: "$recipeTypeName",
        craftTime: "#craftTime",
        ownerBlock: "$ownerBlockName", // default: null
        effFunc: "default:#base", // default: "default:1.0f"
        subInfo: "$subInfoBundleName", // default: null
        materials: [
          {
            item: "$itemName",
            amount: "#number",
            // or item: "$itemName:#number",
            amountFormat: "none"/"integer"/"float"/"persecond"/"raw", // default: "none"
            efficiency: "#efficiency", // default: 1.0f
            isOptional: boolean, // default: false
            isAttribute: boolean, // default: false
            isBooster: boolean, // default: false
            attributeGroup: "$attributeGroupName", // default: null
            maxAttribute: boolean, // default: false
          },
          ...
        ],
        productions: [
          {
            item: "$itemName",
            amount: "#number",
            // or item: "$itemName:#number",
            amountFormat: "none"/"integer"/"float"/"persecond"/"raw", // default: "none"
          },
          ...
        ],
      },
      ...
    ]
  }
  */
  private fun readJsonAPI(mod: LoadedMod) {
    val modMeta = mod.root.child("recipes.json")
    if (!modMeta.exists()) return

    val recipeInfos = Jval.read(modMeta.readString())
    val recipeItems = recipeInfos.get("recipeItems")?.asArray()
    val recipeList = recipeInfos.get("recipeList")?.asArray()

    recipeItems?.forEach{
      val ordinal = it.getInt("ordinal", -1)
      val typeID = it.getInt("typeID", -1)
      val name = it.getString("name", "<error>")
      val localizeNamePath = recipeInfos.getString("localizeNamePath", "name.$name")
      val icon = recipeInfos.getString("icon", "error")
      val hidden = recipeInfos.getBool("hidden", false)

      TooManyItems.itemsManager.addItemWrap(name, object: RecipeItem<String>(name){
        override fun ordinal(): Int = ordinal
        override fun typeID(): Int = typeID
        override fun name(): String = item
        override fun localizedName(): String = Core.bundle[localizeNamePath]
        override fun icon(): TextureRegion = Core.atlas.find(icon)
        override fun hidden(): Boolean = hidden
      })
    }

    recipeList?.forEach{ recipeInfo ->
      val type = recipeInfo.getString("type")
      val ownerBlock: String? = recipeInfo.getString("ownerBlock")
      val craftTime = recipeInfos.getFloat("craftTime", 0f)
      val effFunc = recipeInfo.getString("effFunc", "default:1.0f")
      val subInfo: String? = recipeInfo.getString("subInfo")
      val materials = recipeInfo.get("materials")?.asArray()
      val productions = recipeInfo.get("productions")?.asArray()

      val rawEff = effFunc.split(":")
      val isDefaultEff = rawEff[0] == "default"
      val defBase = rawEff[1].toFloat()

      val recipe = Recipe(
        recipeType = getRecipeType(type),
        craftTime = craftTime,
        ownerBlock = ownerBlock?.let { TooManyItems.itemsManager.getByName<Block>(ownerBlock) }
      ).setEff(
        if (isDefaultEff) Recipe.getDefaultEff(defBase)
        else Recipe.oneEff
      )

      if (subInfo != null){
        recipe.setSubInfo { it.add(Core.bundle[subInfo]) }
      }

      if (materials != null) recipe.materials.putAll(materials.map{
        val stack = parseStack(it)(recipe)
        return@map stack.item to stack
      })

      if (productions != null) recipe.productions.putAll(productions.map{
        val stack = parseStack(it)(recipe)
        return@map stack.item to stack
      })
    }
  }

  private fun parseStack(comp: Jval): ((Recipe) -> RecipeItemStack) {
    val itemRaw = comp.getString("item", "<error>")
    val amount = comp.getFloat("amount", -1f)
    val amountFormat = comp.getString("amountFormat", "none")
    val efficiency = comp.getFloat("efficiency", 1f)
    val isOptional = comp.getBool("isOptional", false)
    val isAttribute = comp.getBool("isAttribute", false)
    val isBooster = comp.getBool("isBooster", false)
    val attributeGroup: String? = comp.getString("attributeGroup", null)
    val maxAttribute = comp.getBool("maxAttribute", false)

    return {
      itemRaw.split(":")
        .let {
          val item = TooManyItems.itemsManager.getByName<Any>(it[0])
          if (it.size == 2) RecipeItemStack(item, it[1].toFloat())
          else RecipeItemStack(item, amount)
        }
        .apply {
          when (amountFormat) {
            "integer" -> integerFormat(it.craftTime)
            "float" -> floatFormat(it.craftTime)
            "persecond" -> persecFormat()
            "rawInt" -> floatFormat()
            "rawFloat" -> floatFormat()
            else -> emptyFormat()
          }
          setEff(efficiency)
          setOptional(isOptional)
          setAttribute(isAttribute)
          setBooster(isBooster)
          setAttribute(attributeGroup)
          setMaxAttr(maxAttribute)
        }
    }
  }

  fun afterInit() {
    for (entry in entries) {
      entry.afterInit()
    }

    Vars.mods.scripts.context.evaluateString(
      Vars.mods.scripts.scope,
      afterInit,
      "tmiAfter.js",
      0
    )
  }
}
