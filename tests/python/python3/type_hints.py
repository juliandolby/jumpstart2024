def greet(name: str) -> str:
    return f"Hello {name}"

greeting = greet("Alice")
print(greeting)
assert greet("Alice") == "Hello Alice"