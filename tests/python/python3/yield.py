def simple_generator():
    yield 42

gen = simple_generator()
print(next(gen))