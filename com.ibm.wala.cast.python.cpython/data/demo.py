def read_logs(file_path):
    with open(file_path, 'r') as f:
        for log in f:
            yield log

def analyze_logs(file_path):
    threat_ips = set()
    log_counts = {}

    for log in read_logs(file_path):
        ip, status, _ = log.split(" - ")
        status = int(status)

        if status == 403:
            threat_ips.add(ip)

        log_counts[ip] = log_counts.get(ip, 0) + 1

        if log_counts[ip] > 1:
            print("Suspicious activity detected from " + ip + "!")

    unused_var = "This variable is never used"
    return threat_ips

print("Threat IPs: " + str(analyze_logs('logs.txt')))

del LOGS