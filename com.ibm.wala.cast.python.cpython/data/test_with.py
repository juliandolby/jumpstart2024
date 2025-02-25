with get_stuff() if needs_with() else nullcontext() as gs:
  print("inside with")

with get_stuff():
  print("inside with no vars")
