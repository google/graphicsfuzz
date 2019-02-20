`run_spvtests.py` runs a series of tests that check `runspv` is behaving
correctly.

The tests assume that you are working under Linux and that an Android device is connected.

They require Python 3, pathlib2, Pillow, and PyTest.

If you are using PyCharm these can all be installed by pressing Alt+Return on the errors about unknown imports and
following the instructions thereafter.

Alternatively, pytest can be installed as follows:

```
sudo apt-get install python3-pip
pip3 install pytest
```

(The other dependencies should be similar).

If using PyCharm you need to [configure it to use PyTest for tests](https://www.jetbrains.com/help/pycharm/pytest.html).