package jp.try0.soc.sample;

/**
 * 
 * @author 
 *
 * 
 */
public class MainProgram {

	/**
	 * @testHints 機能A
	 */
	public void mainMethodA() {
		call();
	}
	
	/**
	 * @testHints 機能B
	 */
	public void mainMethodB() {
		call();
	}

	/**
	 * 
	 * @testHints テストデータ呼び出し処理
	 */
	public void call() {
		TestData data = new TestData("test");
		data.getStr();

		data.tryWithResource();
	}
}
