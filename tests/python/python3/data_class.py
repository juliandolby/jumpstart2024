from dataclasses import dataclass

@dataclass
class Point:
    x: float
    y: float

p = Point(1.0, 2.0)
assert p.x == 1.0
assert p.y == 2.0
print(p)