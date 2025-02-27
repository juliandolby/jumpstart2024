def http_status(code):
    match code:
        case 200:
            return "OK"
        case 404:
            return "Not Found"
        case _:
            return "Unknown"


assert http_status(200) == "OK"
assert http_status(404) == "Not Found"