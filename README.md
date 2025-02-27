# WALA IBM JumpStart 2024 Project
***
## Enabling Python 3.0 Support in WALA
#### Markham Software Labs - Canada
### A New JEP-based WALA Frontend for CPython
The package `com.ibm.wala.cast.python.cpython` implements a new Python language frontend for WALA. Another Python frontend already exists for WALA (`com.ibm.wala.cast.python.jython`) but this implementation is based on the Jython project, which implemented Python language support in the JVM, but does not have support for Python 2.7+. As a replacement, the  new frontend uses JEP to embed CPython in Java. Jep and CPython are actively maintained supporting features from Python 3.0+. We currently use JEP 4.2.2 which supports Python 3.13.
### How to Build