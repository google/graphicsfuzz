`runspv_tests.py` runs a series of tests that check `runspv` is behaving
correctly.

The tests assume that you are working under Linux and that an Android device is connected.

They require Python 3, pathlib2, Pillow, and PyTest.

## Using PyCharm

If you are using PyCharm these can all be installed by pressing Alt+Return on the errors about unknown imports and
following the instructions thereafter.

If using PyCharm you need to [configure it to use PyTest for tests](https://www.jetbrains.com/help/pycharm/pytest.html).

## Using the command line

Alternatively, pytest and dependencies can be installed as follows:

```
python3 -m pip install pytest pathlib2 Pillow
```

To run the tests from the command line:

```
python3 -m pytest ./runspv_tests.py

# Filter to test names containing "foo" but not "bar":
python3 -m pytest ./runspv_tests.py -k 'foo and not bar'
```
