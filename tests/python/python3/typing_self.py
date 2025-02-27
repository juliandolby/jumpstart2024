from typing import Self

class Builder:
    def __init__(self, data: str):
        self.data = data

    def add_suffix(self, suffix: str) -> Self:
        self.data += suffix
        return self

res = Builder("Hello").add_suffix(" World")
print(res.data)
assert res.data == "Hello World"