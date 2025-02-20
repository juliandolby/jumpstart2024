value = 1
match value:
    case 1 if value > 0:
        print('This guard is redundant!')