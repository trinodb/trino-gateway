#!/usr/bin/env python3

import requests
import sys
import yaml

config = '/opt/trino/gateway-ha-config.yml'

scheme = 'http'

with open(config, "r") as cfg_file:
    try:
        cfgs = yaml.safe_load(cfg_file)
        port = cfgs.get('requestRouter').get('port', 8080)
        if cfgs.get('requestRouter').get("ssl", False):
            scheme = 'https'
    except yaml.YAMLError as e:
        print(f"unable to parse input YAML file: {e}", file=sys.stderr)
        exit(1)

endpoint = f"{scheme}://localhost:{port}/api/public/backends"
res = requests.get(endpoint, verify=False)
res.raise_for_status()
