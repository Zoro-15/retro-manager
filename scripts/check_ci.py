import urllib.request
import json
import time
import sys

def check_workflows():
    url = "https://api.github.com/repos/Zoro-15/retro-manager/actions/runs?branch=v2&per_page=10"
    req = urllib.request.Request(url, headers={"User-Agent": "Python", "Accept": "application/vnd.github.v3+json"})
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            runs = data.get("workflow_runs", [])
            if not runs:
                print("No workflow runs found.")
                return []
            
            print(f"{'ID':<14} {'Workflow':<25} {'Status':<12} {'Conclusion':<12} {'Commit':<10} {'Head Message'}")
            print("-" * 80)
            for r in runs[:6]:
                head_sha = r.get("head_sha", "")[:8]
                msg = r.get("head_commit", {}).get("message", "").split("\n")[0][:30]
                print(f"{r['id']:<14} {r['name']:<25} {r['status']:<12} {str(r['conclusion']):<12} {head_sha:<10} {msg}")
            return runs
    except Exception as e:
        print(f"Error fetching runs: {e}")
        return []

if __name__ == "__main__":
    check_workflows()
