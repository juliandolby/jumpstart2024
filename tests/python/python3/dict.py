numbers = [1, 2, 3, 4]
squares = {n: n*n for n in numbers}
print(squares)
assert squares == {1: 1, 2: 4, 3: 9, 4: 16}