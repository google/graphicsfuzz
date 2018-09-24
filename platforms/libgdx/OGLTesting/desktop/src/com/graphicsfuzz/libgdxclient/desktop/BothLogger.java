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

package com.graphicsfuzz.libgdxclient.desktop;

import com.badlogic.gdx.ApplicationLogger;

public class BothLogger implements ApplicationLogger {

    private void println(String line){
        System.err.println(line);
        System.err.flush();
        System.out.println(line);
    }

    private void printStackTrace(Throwable exception){
        exception.printStackTrace(System.out);
        exception.printStackTrace(System.err);
    }

    @Override
    public void log (String tag, String message) {
        this.println(tag + ": " + message);
    }

    @Override
    public void log (String tag, String message, Throwable exception) {
        this.println(tag + ": " + message);
        exception.printStackTrace(System.out);
    }

    @Override
    public void error (String tag, String message) {
        this.println(tag + ": " + message);
    }

    @Override
    public void error (String tag, String message, Throwable exception) {
        this.println(tag + ": " + message);
        this.printStackTrace(exception);
    }

    @Override
    public void debug (String tag, String message) {
        this.println(tag + ": " + message);
    }

    @Override
    public void debug (String tag, String message, Throwable exception) {
        this.println(tag + ": " + message);
        this.printStackTrace(exception);
    }
}