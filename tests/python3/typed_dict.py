from typing import TypedDict

class Label(TypedDict):
    name: str
    value: int

m: Label = { "name": "Foo", "value": 100 }
print(m)
assert m["name"] == "Foo"
assert m["value"] == 100
