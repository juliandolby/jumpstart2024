LOGS = [
    "192.168.1.1 - 200 - OK",
    "10.0.0.1 - 403 - Forbidden",
    "192.168.1.2 - 500 - Server Error",
    "192.168.1.1 - 403 - Forbidden",
]

def read_logs(logs):
    for log in logs:
        yield log

def analyze_logs(logs):
    threat_ips = set()
    log_counts = {}

    for log in read_logs(logs):
        ip, status, _ = log.split(" - ")
        status = int(status)

        if status == 403:
            threat_ips.add(ip)

        log_counts[ip] = log_counts.get(ip, 0) + 1

        if log_counts[ip] > 1:
            print("Suspicious activity detected from " + ip + "!")

    unused_var = "This variable is never used"
    return threat_ips

print("Threat IPs: " + str(analyze_logs(LOGS)))

del LOGS
