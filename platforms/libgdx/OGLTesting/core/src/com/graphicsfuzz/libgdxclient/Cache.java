/*
 * Copyright 2018 The GraphicsFuzz Project Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.graphicsfuzz.libgdxclient;

import java.util.ArrayDeque;

public class Cache<K,V> {

  private static class Entry<K,V> {
    private K key;
    private V val;

    public Entry(K key, V val) {
      this.key = key;
      this.val = val;
    }
  }

  private final ArrayDeque<Entry<K,V>> cache = new ArrayDeque<Entry<K, V>>();
  private final int capacity;

  public Cache(int capacity) {
    this.capacity = capacity;
  }

  public V get(K key) {
    for (Entry<K, V> entry : cache) {
      if(entry.key.equals(key)) {
        return entry.val;
      }
    }
    return null;
  }

  public void add(K key, V val) {
    if(get(key) != null) return;

    cache.add(new Entry<K, V>(key, val));
    while(cache.size() > capacity) {
      cache.pop();
    }
  }

}
