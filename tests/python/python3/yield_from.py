def gen():
    for i in range(3):
        yield i

def generate():
    yield from gen()
    yield "Done"

for val in generate():
    print(val)