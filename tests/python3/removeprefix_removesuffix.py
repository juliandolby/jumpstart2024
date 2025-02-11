text = "unwanted_prefix_value_unwanted_suffix"
no_prefix = text.removeprefix("unwanted_prefix_")
assert no_prefix == "value_unwanted_suffix"
no_suffix = no_prefix.removesuffix("_unwanted_suffix")
assert no_suffix == "value"
print(no_suffix)