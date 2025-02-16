x = 1
print(x)

def func():
    global x
    x = 2
    print(x)

func()
print(x)