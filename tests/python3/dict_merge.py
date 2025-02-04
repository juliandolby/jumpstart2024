a = {"w": 1, "y": 2, "z": 3}
b = {"x": 4, "y": 5, "z": 6}
merge_into_a = a | b
print(merge_into_a)
merge_into_b = b | a
print(merge_into_b)
assert merge_into_a == {"w": 1, "x": 4, "y": 5, "z": 6}
assert merge_into_b == {"w": 1, "x": 4, "y": 2, "z": 3}
