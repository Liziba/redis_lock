package com.lizba.controller;

import cn.hutool.core.util.IdUtil;
import com.lizba.utill.RedisLockUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>
 *
 * </p>
 *
 * @Author: Liziba
 * @Date: 2021/7/11 12:27
 */
@RestController
@RequestMapping("/redis")
public class TestController {

    @Autowired
    private RedisLockUtil redisLockUtil;

    private AtomicInteger count ;

    @GetMapping("/index/{num}")
    public String index(@PathVariable int num) throws InterruptedException {
        count = new AtomicInteger(0);
        CountDownLatch countDownLatch = new CountDownLatch(num);
        ExecutorService executorService = Executors.newFixedThreadPool(num);

        Set<String> failSet = new HashSet<>();

        long start = System.currentTimeMillis();
        for (int i = 0; i < num; i++) {
            executorService.execute(() -> {
                long lockId = IdUtil.getSnowflake(1, 1).nextId();
                try {
                    boolean isSuccess = redisLockUtil.timeLock(String.valueOf(lockId));
                    if (isSuccess) {
                        count.addAndGet(1);
                        System.out.println(Thread.currentThread().getName() + "  lock success" );
                    } else {
                        failSet.add(Thread.currentThread().getName());
                    }
                } finally {
                    boolean unlock = redisLockUtil.unlock(String.valueOf(lockId));
                    if (unlock) {
                        System.out.println(Thread.currentThread().getName() + "  unlock success" );
                    }
                }
                countDownLatch.countDown();
            });
        }
        countDownLatch.await();
        executorService.shutdownNow();
        failSet.forEach(t -> System.out.println(t + "  lock fail" ));
        long time = System.currentTimeMillis() - start;
        return String.format("Thread sum: %d, Time sum: %d, Success sum：%d", num, time, count.get());
    }

}
