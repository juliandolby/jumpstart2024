x = 0
print(x)
def func1():
    x = 1
    print(x)
    def func2():
        nonlocal x
        x = 2
        print(x)
    def func3():
        x = 5
        print(x)
    func3()
    print(x)
    func2()
    print(x)

func1()
print(x)