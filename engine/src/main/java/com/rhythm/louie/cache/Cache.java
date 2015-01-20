/* 
 * Copyright 2015 Rhythm & Hues Studios.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.rhythm.louie.cache;

import java.util.Map;

/**
 * @author cjohnson
 * Created: Feb 10, 2011 2:03:55 PM
 */
public interface Cache<K, V> {

    void put(K key, V value) throws Exception;

    V get(K key);

    void remove(K key) throws Exception;

    void clear() throws Exception;

    String getCacheName();
    
    void putAll(Map<K,V> map) throws Exception;
    
    int getSize();
}
