class Matrix:
    def __init__(self, value):
        self.value = value

    def __matmul__(self, other):
        return Matrix(self.value * other.value)

m1 = Matrix(2)
m2 = Matrix(3)
m3 = m1 @ m2
print(m3.value)
assert m3.value == 6