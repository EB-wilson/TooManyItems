package tmi.recipe.types

/**配方条目的类型，在计算效率时决定时该条目所在的计算区，默认的效率计算公式如下：
 *
 * ```
 * mul = (base + ATTRIBUTE)*POWER
 * eff = NORMAL*max(BOOSTER, 1)*mul
 * consumes = base*mul
 * ```
 *
 * 其中，除去`ATTRIBUTE`, `POWER`，`NORMAL`和`BOOSTER`计算区外的各变量：
 * - `mul`为基础倍增器，它由属性计算区和基础效率相加获得计算结果
 * - `base`为基础效率，通常为1
 * - `eff`为配方的最终效率，由常规乘区的计算结果与倍增器乘算获得
 * - `consumes`表示配方的消耗量，base为基础消耗，它会与基础倍率进行乘算
 *
 * 各计算区采用的计算方法在配方中定义*/
enum class RecipeItemType {
  //General
  /**普通条目，作为消耗项时该条目用于NORMAL计算区*/
  NORMAL,
  /**能量条目，作为材料时该条目用于POWER计算区*/
  POWER,

  //Consumes
  /**属性条目，在计算效率时用于ATTRIBUTE计算区*/
  ATTRIBUTE,
  /**增幅条目，在计算效率时用于BOOSTER计算区*/
  BOOSTER,

  //Products
  /**表示该产品为副产物*/
  SIDEPRODUCT,
  /**概率产出条目，表示该输出条目为随生产概率产出的产物*/
  PROBABILITY,
  /**表示该条目为产出的需要清理的废料*/
  GARBAGE,
}