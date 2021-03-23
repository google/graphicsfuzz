/*
 * Copyright 2019 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.common.util;

import com.graphicsfuzz.parser.GLSLParser;

public class GlslParserException extends Exception {

    private final GLSLParser parser;

    public GlslParserException(GLSLParser parser) {
        this.parser = parser;
    }

    @Override
    public String getMessage() {
        return "Syntax errors occurred during parsing.";
    }

    /**
     * Provides access to the parser instance that failed to parse the GLSL shader.
     *
     * @return Parser instance.
     */
    public GLSLParser getParser() {
        return parser;
    }

}
