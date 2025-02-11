x = 1
print(x)

def func1():
    x = x + 1
    def func2():
        nonlocal x
        print(x)
        x = x + 1

        def func3():
            print(x)

            def func4():
                global x
                print(x)
            
            func4()
        
        func3()
    
    func2()

func1()
print(x)