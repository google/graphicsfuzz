@echo off

@REM
@REM  Copyright 2018 The GraphicsFuzz Project Authors
@REM
@REM  Licensed under the Apache License, Version 2.0 (the "License");
@REM  you may not use this file except in compliance with the License.
@REM  You may obtain a copy of the License at
@REM
@REM      https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM  Unless required by applicable law or agreed to in writing, software
@REM  distributed under the License is distributed on an "AS IS" BASIS,
@REM  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM  See the License for the specific language governing permissions and
@REM  limitations under the License.
@REM

IF DEFINED PYTHON_GF (
  "%PYTHON_GF%" "%~dpn0.py" %*
) ELSE (
  where /q py
  IF %ERRORLEVEL% EQU 0 (
    py -3 "%~dpn0.py" %*
  ) ELSE (
    where /q python3
    IF %ERRORLEVEL% EQU 0 (
      python3 "%~dpn0.py" %*
    ) ELSE (
      python "%~dpn0.py" %*
    )
  )
)
