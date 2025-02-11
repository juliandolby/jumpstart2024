def outer():
    x = 10
    def inner():
        nonlocal x
        x += 5
        return x
    res = inner()
    print(res)
    assert res == 15
    assert x == 15

outer()