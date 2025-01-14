/*
 * Copyright 2020 ICONLOOP Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iconloop.score.test;

import score.Address;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Stack;

public class ServiceManager {
    private static final BigInteger ICX = BigInteger.TEN.pow(18);

    private final Stack<Frame> contexts = new Stack<>();
    private final Map<Address, Score> addressScoreMap = new HashMap<>();
    private final Map<String, Object> storageMap = new HashMap<>();
    private final Map<String, Class<?>> storageClassMap = new HashMap<>();
    private int nextCount = 1;

    public Score deploy(Account owner, Class<?> mainClass, Object... params) throws Exception {
        getBlock().increase();
        var score = new Score(Account.newScoreAccount(nextCount++), owner);
        addressScoreMap.put(score.getAddress(), score);
        pushFrame(owner, score.getAccount(), false, "<init>", BigInteger.ZERO);
        try {
            Constructor<?>[] ctor = mainClass.getConstructors();
            if (ctor.length != 1) {
                // User SCORE should only have one public constructor
                throw new AssertionError("multiple public constructors found");
            }
            score.setInstance(ctor[0].newInstance(params));
        } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
            throw e;
        } finally {
            popFrame();
        }
        return score;
    }

    public Account createAccount() {
        return createAccount(0);
    }

    public Account createAccount(int initialIcx) {
        var acct = Account.newExternalAccount(nextCount++);
        acct.addBalance("ICX", ICX.multiply(BigInteger.valueOf(initialIcx)));
        return acct;
    }

    public Address getOwner() {
        var address = getCurrentFrame().to.getAddress();
        return getScoreFromAddress(address).getOwner().getAddress();
    }

    public Address getOrigin() {
        return getFirstFrame().from.getAddress();
    }

    public Address getCaller() {
        return getCurrentFrame().from.getAddress();
    }

    public Address getAddress() {
        return getCurrentFrame().to.getAddress();
    }

    private Score getScoreFromAddress(Address target) {
        var score = addressScoreMap.get(target);
        if (score == null) {
            throw new IllegalStateException("ScoreNotFound");
        }
        return score;
    }

    public Object call(Account from, BigInteger value, Address targetAddress, String method, Object... params) {
        Score score = getScoreFromAddress(targetAddress);
        return score.call(from, false, value, method, params);
    }

    public Object call(BigInteger value, Address targetAddress, String method, Object... params) {
        Score from = getScoreFromAddress(getAddress());
        if ("fallback".equals(method) || "".equals(method)) {
            transfer(from.getAccount(), targetAddress, value);
            return null;
        } else {
            return call(from.getAccount(), value, targetAddress, method, params);
        }
    }

    public void transfer(Account from, Address targetAddress, BigInteger value) {
        getBlock().increase();
        var fromBalance = from.getBalance();
        if (fromBalance.compareTo(value) < 0) {
            throw new IllegalStateException("OutOfBalance");
        }
        var to = Account.getAccount(targetAddress);
        if (to == null) {
            throw new IllegalStateException("NoAccount");
        }
        from.subtractBalance("ICX", value);
        to.addBalance("ICX", value);
        if (targetAddress.isContract()) {
            call(from, value, targetAddress, "fallback");
        }
    }

    public void putStorage(String key, Object value) {
        putStorage(key, value, value != null ? value.getClass() : null);
    }

    public void putStorage(String key, Object value, Class<?> clazz) {
        storageMap.put(getAddress().toString() + key, value);
        storageClassMap.put(getAddress().toString() + key, clazz);
    }

    public Object getStorage(String key) {
        return storageMap.get(getAddress().toString() + key);
    }

    public Class<?> getStorageClass(String key) {
        return storageClassMap.get(getAddress().toString() + key);
    }

    public static class Block {
        private static Block sInstance;

        private long height;
        private long timestamp;

        private Block(long height, long timestamp) {
            this.height = height;
            this.timestamp = timestamp;
        }

        public static Block getInstance() {
            if (sInstance == null) {
                Random rand = new Random();
                sInstance = new Block(rand.nextInt(1000), System.nanoTime() / 1000);
            }
            return sInstance;
        }

        public long getHeight() {
            return height;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void increase() {
            increase(1);
        }

        public void increase(long delta) {
            height += delta;
            timestamp += 2_000_000 * delta; // 2 secs block generation
        }
    }

    public Block getBlock() {
        return Block.getInstance();
    }

    public static class Frame {
        Account from;
        Account to;
        String method;
        boolean readonly;
        BigInteger value;

        public Frame(Account from, Account to, boolean readonly, String method, BigInteger value) {
            this.from = from;
            this.to = to;
            this.readonly = readonly;
            this.method = method;
            this.value = value;
        }

        public boolean isReadonly() {
            return readonly;
        }

        public BigInteger getValue() {
            return value;
        }
    }

    protected void pushFrame(Account from, Account to, boolean readonly, String method, BigInteger value) {
        contexts.push(new Frame(from, to, readonly, method, value));
    }

    protected void popFrame() {
        contexts.pop();
    }

    public Frame getCurrentFrame() {
        return contexts.peek();
    }

    public Frame getFirstFrame() {
        return contexts.firstElement();
    }
}
