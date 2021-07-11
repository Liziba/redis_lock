package com.lizba.utill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *       Redis分布式锁简单工具类
 * </p>
 *
 * @Author: Liziba
 * @Date: 2021/7/11 11:42
 */
@Service
public class RedisLockUtil {

    private static Logger logger = LoggerFactory.getLogger(RedisLockUtil.class);
    /**
     * 锁键 -> key
     */
    private final String LOCK_KEY = "lock_key";
    /**
     * 锁过期时间 -> TTL
     */
    private Long millisecondsToExpire = 10000L;
    /**
     * 获取锁超时时间 -> get lock timeout for return
     */
    private Long timeout = 300L;
    /**
     * LUA脚本 -> 分布式锁解锁原子操作脚本
     */
    private static final String LUA_SCRIPT =
            "if redis.call('get',KEYS[1]) == ARGV[1] then" +
                    " return redis.call('del',KEYS[1]) " +
                    "else" +
                    " return 0 " +
                    "end";
    /**
     * set命令参数
     */
    private SetParams params = SetParams.setParams().nx().px(millisecondsToExpire);

    @Autowired
    private JedisPool jedisPool;


    /**
     * 加锁 -> 超时锁
     *
     * @param lockId  一个随机的不重复id -> 区分不同客户端
     * @return
     */
    public boolean timeLock(String lockId) {
        Jedis client = jedisPool.getResource();
        long start = System.currentTimeMillis();
        try {
            for(;;) {
                String lock = client.set(LOCK_KEY, lockId, params);
                if ("OK".equalsIgnoreCase(lock)) {
                    return Boolean.TRUE;
                }
                // sleep -> 获取失败暂时让出CPU资源
                TimeUnit.MILLISECONDS.sleep(100);
                long time = System.currentTimeMillis() - start;
                if (time >= timeout) {
                    return Boolean.FALSE;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.getMessage());
        } finally {
            client.close();
        }
        return Boolean.FALSE;
    }



    /**
     * 解锁
     *
     * @param lockId 一个随机的不重复id -> 区分不同客户端
     * @return
     */
    public boolean unlock(String lockId) {
        Jedis client = jedisPool.getResource();
        try {
            Object result = client.eval(LUA_SCRIPT, Arrays.asList(LOCK_KEY), Arrays.asList(lockId));
            if (result != null && "1".equalsIgnoreCase(result.toString())) {
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.getMessage());
        }
        return Boolean.FALSE;
    }


}
