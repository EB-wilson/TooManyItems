package tmi.ui.calculator

/**可回滚事务：一次用户操作被封装成一个对象，[commit] 执行它，[rollback] 撤销它。
 *
 * 该接口只描述「执行」与「回滚」两件事，具体实现见 [CalculatorTransactions]。
 * 同一个实例可以被反复 commit/rollback（例如撤销后再重做），实现需要保证这两种调用
 * 都能把模型带到正确的状态，而不是只依赖一次性的副作用。
 *
 * @author EBwilson */
interface ITransaction {
  /**执行此事务。*/
  fun commit()

  /**回滚此事务，恢复到事务生效之前的状态。*/
  fun rollback()
}
