package edu.ucsd.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class OnDemandLoader {	
	private static Map<String, ReadWriteLock> loadingLocks;

	static{
		loadingLocks = new HashMap<String, ReadWriteLock>();
	}

	private synchronized static Lock getWriteLock(String resource){
		if(!loadingLocks.containsKey(resource))
			loadingLocks.put(resource, new ReentrantReadWriteLock());
		return loadingLocks.get(resource).writeLock();
	}

	private synchronized static void releaseLock(String resource){
		loadingLocks.remove(resource);
	}
	
	private synchronized static Lock getReadLock(String resource){
		ReadWriteLock lock = loadingLocks.get(resource);
		return (lock == null) ? null : lock.readLock();
	}
	
	public static boolean load(OnDemandOperation loader){
		String name = loader.getResourceName();
		if(!loader.resourceExists() || loader.resourceDated()){
			Lock write = getWriteLock(name);
			if(write.tryLock())
				try{return loader.execute(); }
				finally{
					write.unlock();
					releaseLock(name);
				}
		}
		Lock read = getReadLock(name);
		if(read != null)
			try{ read.lock(); } finally{ read.unlock(); }
		return true;
	}
}
