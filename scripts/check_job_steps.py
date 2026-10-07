import urllib.request
import json
import sys

def check_jobs(run_id):
    url = f"https://api.github.com/repos/Zoro-15/retro-manager/actions/runs/{run_id}/jobs"
    req = urllib.request.Request(url, headers={"User-Agent": "Python", "Accept": "application/vnd.github.v3+json"})
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            for job in data.get("jobs", []):
                print(f"Job: {job['name']} | Status: {job['status']} | Conclusion: {job['conclusion']}")
                for step in job.get("steps", []):
                    print(f"  - Step: {step['name']:<40} [{step['status']}] -> {step.get('conclusion')}")
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    if len(sys.argv) > 1:
        check_jobs(sys.argv[1])
    else:
        check_jobs("37593466517")
        print("=" * 60)
        check_jobs("37593466032")
