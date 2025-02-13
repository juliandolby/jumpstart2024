# --------
# Literals
# --------
literal = 1
match literal:
    case 1:
        print('the literal is 1!')
    # Wildcard, catches anything and ignores it
    case _:
        print('the literal is some other value!')

# ---------
# Sequences
# ---------
sequence = ['one', 'two', 'three', 'four']
match sequence:
    case ['one']:
        print('the sequence has only one element!')
    # Tuples and lists are treated the same
    case ('one', 'two', 'three', 'four'):
        print("the sequence has four elements!")

match sequence:
    # Match statements can bind variables,
    # including gathering the rest of a sequence into one variable using *
    case [a, b, *rest]:
        print(f'the sequence starts with \'{a}\', then \'{b}\', then continues with {rest}')
    case _:
        print('something else happened!')

# More sequence matches, but with some fancier composed patterns
sequence = ['one', ('left', 'right'), 'blue', 'dog', 'firetruck']
match sequence:
    case [first, (a, b), _, *end]:
        print(f'the sequence starts with \'{first}\', followed by a tuple (\'{a}\', \'{b}\'), followed by some element we don\'t care about, and then finishes with {end}')
    case _:
        print('something else happened!')

# --------
# Or-match
# --------
velocity = (90, 'east')
match velocity:
    case (_, 'north' | 'south' | 'east' | 'west'):
        print('valid direction!')
    case (_, _):
        print('invalid direction :(')

# What if we want to capture a matched subpattern?
# That is, refer to the direction, but still ensure that it is valid?
match velocity:
    case (_, 'north' | 'south' | 'east' | 'west' as direction):
        print(f'{direction} is a valid direction!')
    case (_, direction):
        print(f'{direction} is not a valid direction :(')

# -------
# Classes
# -------
class Rectangle:
    def __init__(self, w, h):
        self.width = w
        self.height = h

rect1 = Rectangle(5, 7)
match rect1:
    # Note the need to specify the fields you're matching,
    # and also that you don't need to match on every field
    case Rectangle(height=h):
        print(f'rect1 is a Rectangle with a height of {h}!')
    case _:
        print('rect1 is something else (maybe not even a Rectangle)')

# With the __match_args__ special attribute,
# you can set an order for the fields in your class so you don't have to specify them in the pattern
class Point:
    __match_args__ = ('x', 'y')
    def __init__(self, x, y):
        self.x = x
        self.y = y

pt1 = Point(0, 0)
match pt1:
    case Point(0, 0):
        print('this point is at the origin!')
    case Point(_, _):
        print('this point is not at the origin!')
    case _:
        print('this is something else, (maybe not even a point)')

# ------
# Guards
# ------
match pt1:
    case Point(x, y) if x == y:
        print('this point lies on the line y = x')
    case Point(_, _):
        print('this point does not lie on the line y = x')
    case _:
        print('this is something else, (maybe not even a point)')

# -----
# Enums
# -----
from enum import Enum
class Color(Enum):
    RED = 0
    GREEN = 1
    BLUE = 2

color = Color.RED
match color:
    case Color.RED:
        print('red!')
    case Color.GREEN:
        print('green!')
    case Color.BLUE:
        print('blue!')

# ------------
# Dictionaries
# ------------
class LogLevel(Enum):
    INFO = 0
    WARNING = 1
    ERROR = 2

log_msg = {'level': LogLevel.INFO, 'message': 'everything is working as expected!'}
match log_msg:
    case {'level': LogLevel.INFO, 'message': message}:
        print(f'here is an info message: \'{message}\'')
    case _:
        print('log_msg is something else')

# Like sequences, you can gather the rest of the dictionary into one variable, but this time using an **
match log_msg:
    case {'level': LogLevel.INFO, **rest}:
        print(f'here is the rest of the log message: {rest}')
    case _:
        print('log_msg is something else')

# Like classes but unlike sequences, you can choose to ignore parts of the dictionary
match log_msg:
    case {'level': LogLevel.INFO}:
        print('this log message has level INFO')
    case _:
        print('log_msg is something else')