def test_func(x):
    try:
        result = 10/x
    except ZeroDivisionError:
        print("Caught ZeroDivisionError")
    else:
        print("No error, result is", result)
    finally:
        print("Executing finally block")
test_func(2)
test_func(0)