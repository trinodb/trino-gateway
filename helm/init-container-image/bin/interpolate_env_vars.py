import argparse
import re
from os import getenv


def do_interpolation(input_filename: str, output_filename: str):
    env_pattern = re.compile('\\$\\{ENV:\s*([a-zA-Z0-0_]*)\s*\\}')

    input = open(input_filename, 'r')
    output = open(output_filename, 'w')

    for line in input:
        match = env_pattern.search(line)
        if match:
            print(f'Substituting environment variable {match.groups()[0]}')
            replacement = getenv(match.groups()[0])
            if replacement is None:
                raise Exception(f'ENV {match.groups()[0]} not found')
            line = re.sub(env_pattern, replacement, line)
        output.write(line)

    input.close()
    output.close()


if __name__ == '__main__':

    parser = argparse.ArgumentParser()
    parser.add_argument('input')
    parser.add_argument('output')
    args = parser.parse_args()
    do_interpolation(args.input, args.output)
