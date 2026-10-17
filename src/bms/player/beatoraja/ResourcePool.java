package bms.player.beatoraja;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/**
 * リソースプール。イメージデータやオーディオデータ等の読み込みコストが大きく、
 * なおかつ明示的な解放が必要なリソースをプールする仕組みを提供する。
 * 
 * @author exch
 *
 * @param <K> リソースを取り出すためのキー
 * @param <V> リソース
 */
public abstract class ResourcePool<K, V> implements Disposable {
	/**
	 * リソースの最大世代数
	 */
	private final int maxgen;
	/**
	 * リソース
	 */
	private ConcurrentHashMap<K, ResourceCacheElement<V>> resourceMap = new ConcurrentHashMap<K, ResourceCacheElement<V>> ();
	/**
	 * disposeOld後に保持するリソースの合計サイズ上限(byte)。0以下は無制限
	 */
	private long maxBytes;

	public ResourcePool(int maxgen) {
		this.maxgen = maxgen;
	}

	/**
	 * disposeOld後に保持するリソースの合計サイズ上限を設定する。超過分は古い世代から開放する
	 *
	 * @param maxBytes 上限(byte)。0以下は無制限
	 */
	public void setMaxBytes(long maxBytes) {
		this.maxBytes = maxBytes;
	}

	/**
	 * 指定するキーの要素が存在する場合はtrueを返す
	 *
	 * @param key リソースのキー
	 * @return キーに対応するリソースが存在する場合はtrue
	 */
	public boolean exists(K key) {
		return resourceMap.containsKey(key);
	}

	/**
	 * 指定したキーに対応するリソースを取得する。リソースがプールにない場合はloadを呼び出して
	 * リソースを取得し、プールに登録した上でリソースを返す。
	 *
	 * @param key リソースのキー
	 * @return リソース。読めなかった場合はnullを返す
	 */
 	public V get(K key) {
 		ResourceCacheElement<V> ie = resourceMap.get(key);
		if(ie == null) {
			V resource = load(key);
			if(resource != null) {
				ie = new ResourceCacheElement<V>(resource, sizeOf(resource));
				resourceMap.put(key, ie);
				if(maxBytes > 0) {
					// 読み込み中も上限を守る。呼び出し元が直後に使う今回のリソースは開放しない
					disposeOverBudget(key);
				}
			}
		} else {
			ie.gen = 0;
		} 			
		
		return ie != null ? ie.resource : null;
	}
 	
 	private final Array<K> removes = new Array<K>();

 	/**
 	 * 世代数を進め、最大世代数を経過したリソースを開放する
 	 */
	public void disposeOld() {
		removes.clear();
		resourceMap.forEach((key, value) -> {
			if(value.gen == maxgen) {
				dispose(value.resource);
				removes.add(key);
			} else {
				value.gen++;
			}			
		});
		
		for(K key : removes) {
			resourceMap.remove(key);
		}
		if(maxBytes > 0) {
			disposeOverBudget(null);
		}
	}

	/**
	 * 合計サイズがmaxBytesを超えている間、古い世代(同世代は大きいもの)から開放する
	 *
	 * @param keep 開放しないリソースのキー。nullの場合は全て対象
	 */
	private void disposeOverBudget(K keep) {
		long total = 0;
		final List<Map.Entry<K, ResourceCacheElement<V>>> entries = new ArrayList<>(resourceMap.entrySet());
		for(Map.Entry<K, ResourceCacheElement<V>> e : entries) {
			total += e.getValue().size;
		}
		if(total <= maxBytes) {
			return;
		}
		entries.sort((a, b) -> a.getValue().gen != b.getValue().gen ? Integer.compare(b.getValue().gen, a.getValue().gen)
				: Long.compare(b.getValue().size, a.getValue().size));
		for(Map.Entry<K, ResourceCacheElement<V>> e : entries) {
			if(total <= maxBytes) {
				break;
			}
			if(e.getKey().equals(keep)) {
				continue;
			}
			if(resourceMap.remove(e.getKey(), e.getValue())) {
				dispose(e.getValue().resource);
				total -= e.getValue().size;
			}
		}
	}

	/**
	 * 現在のリソースの要素数を返す。
	 * @return リソースの
	 */
	public int size() {
		return resourceMap.size();
	}
	
	public void dispose() {
		resourceMap.forEach((key, value) -> {
			dispose(value.resource);			
		});
		resourceMap.clear();
	}
	
	/**
	 * リソースを読み込む
	 * @param key リソースのキー
	 * @return リソース。読めなかった場合はnullを返す
	 */
	protected abstract V load(K key);

	/**
	 * リソースを開放する
	 * @param resource 開放するリソース
	 */
	protected abstract void dispose(V resource);

	/**
	 * リソースのおおよそのメモリサイズ(byte)。maxBytesによる開放に使う
	 * @param resource リソース
	 * @return サイズ。不明な場合は0
	 */
	protected long sizeOf(V resource) {
		return 0;
	}

	/**
	 * リソース
	 *
	 * @param <R>
	 */
	private static class ResourceCacheElement<R> {
		/**
		 * リソース
		 */
		public final R resource;
		/**
		 * 世代
		 */
		public int gen;
		/**
		 * サイズ(byte)
		 */
		public final long size;
		
		public ResourceCacheElement(R resource, long size) {
			this.resource = resource;
			this.size = size;
		}
	}
}