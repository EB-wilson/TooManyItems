package tmi.recipe.types

/**配方条目的类型，在计算效率时决定时该条目所在的计算区，默认的效率计算公式如下：
 *
 * ```
 * mul = (base + ATTRIBUTE)*POWER
 * eff = NORMAL*max(BOOSTER, 1)*mul*ISOLATE
 * consumeMul = base*mul
 * productMul = base*eff
 * ```
 *
 * 其中，除去`ATTRIBUTE`, `POWER`，`NORMAL`，`ISOLATE`和`BOOSTER`计算区外的各变量：
 * - `mul`为基础倍增器，它由属性计算区和基础效率相加获得计算结果
 * - `base`为基础效率，通常为1
 * - `eff`为配方的最终效率，由常规乘区的计算结果与倍增器乘算获得
 * - `consumeMul`表示配方的标准消耗项倍数，base为基础消耗，它会与基础倍率进行乘算
 * - `productMul`表示配方的标准产出物倍数，同上
 *
 * 特别的，`ISOLATE`计算时不乘以任何效率或倍率。
 *
 * 各计算区采用的计算方法在配方中定义*/
enum class RecipeItemType {
  //General
  /**普通条目，作为消耗项时该条目用于NORMAL计算区*/
  NORMAL,
  /**孤立条目，作为消耗项时用于ISOLATE乘区，消耗数量不受配方工作效率的影响，倍率与效率均不作用于此类条目的消耗量*/
  ISOLATED,
  /**能量条目，作为材料时该条目用于POWER计算区*/
  POWER,
  /**特殊条目，由配方类型进行特殊解析，通常不参与常规处理*/
  SPECIAL,

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