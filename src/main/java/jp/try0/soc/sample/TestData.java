package jp.try0.soc.sample;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;

/**
 * クラスコメント。
 * 
 * @author 
 * @testHints 〇〇のテスト機能用
 *
 */
public class TestData implements Serializable {

	public static class StaticInnerTestData implements AutoCloseable {

		public void close() throws Exception {
		}

		public void run() throws IOException {

		}

	}

	public static void closeQuitely(Closeable c) {
		try {
			c.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String str;

	public TestData(String str) {
		this.str = str;
	}

	public String getStr() {
		return this.str;
	}

	public void setStr(String str) {
		this.str = str;
	}

	@TestAnotation
	public void hasAnnotateMethod() {
	}

	/**
	 * @testHints 〇〇の内部処理
	 */
	private void privateMethod() {
		var str = getStr();
	}

	protected void tryCatch() {
		var sitd = new StaticInnerTestData();
		try {
			sitd.run();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				sitd.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	protected void tryWithResource() {

		try (var ac = new StaticInnerTestData()) {

		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}
