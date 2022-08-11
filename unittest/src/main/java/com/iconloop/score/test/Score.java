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
import score.UserRevertedException;

import java.util.*;
import scorex.util.ArrayList;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;

public class Score extends TestBase {
    private static final ServiceManager sm = getServiceManager();

    private final Account score;
    private final Account owner;
    private Object instance;

    public Score(Account score, Account owner) {
        this.score = score;
        this.owner = owner;
    }

    public Account getAccount() {
        return this.score;
    }

    public Address getAddress() {
        return this.score.getAddress();
    }

    public Account getOwner() {
        return this.owner;
    }

    public void setInstance(Object newInstance) {
        this.instance = newInstance;
    }

    public Object getInstance() {
        return this.instance;
    }

    public Object call(String method, Object... params) {
        return call(null, true, BigInteger.ZERO, method, params);
    }

    public void invoke(Account from, String method, Object... params) {
        sm.getBlock().increase();
        call(from, false, BigInteger.ZERO, method, params);
    }

    Object call(Account from, boolean readonly, BigInteger value, String method, Object... params) {
        sm.pushFrame(from, this.score, readonly, method, value);
        Class<?>[] paramClasses = new Class<?>[params.length];
        for (int i = 0; i < params.length; i++) {
            params[i] = preProccessParam(params[i]);
            Class<?> type = params[i].getClass();
    
            // Convert supported object types to primitive data types
            if (type == Integer.class) {
                paramClasses[i] = Integer.TYPE; // int
            } else if (type == Long.class) {
                paramClasses[i] = Long.TYPE; // long
            } else if (type == Short.class) {
                paramClasses[i] = Short.TYPE; // short
            } else if (type == Character.class) {
                paramClasses[i] = Character.TYPE; // char
            } else if (type == Byte.class) {
                paramClasses[i] = Byte.TYPE; // byte
            } else if (type == Boolean.class) {
                paramClasses[i] = Boolean.TYPE; // boolean
            } else {
                paramClasses[i] = type;
            }
        }
        try {
            Class<?> clazz = instance.getClass();
            var m = clazz.getMethod(method, paramClasses);
            return m.invoke(instance, params);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            var target = e.getCause();
            if (target instanceof UserRevertedException
                    && sm.getCurrentFrame() != sm.getFirstFrame()) {
                throw (UserRevertedException) target;
            }
            throw new AssertionError(target.getMessage());
        } finally {
            sm.popFrame();
        }
    }

    private Object preProccessParam(Object param) {
        try {
            Class<?> type = param.getClass();
            if (type == List.class || 
                type == ArrayList.class || 
                type == Class.forName("java.util.ImmutableCollections$ListN")) {
                List<?> list = (List<?>) param;
                if (list.size() == 0) {
                    return param;
                }
    
                Class<?> listType = list.get(0).getClass();
                Object[] arr = (Object[]) Array.newInstance(listType, list.size());
                for (int j = 0; j < list.size(); j++) {
                    arr[j] = list.get(j);   
                }
    
                return arr;
            }
        } catch (ClassNotFoundException e) {
        }

        return param;    
    }
}
