#!/usr/bin/env python3
import sys
import os
import argparse
import yaml
from yaml import Loader, Dumper, SafeLoader, SafeDumper
from glob import glob
from openapi_spec_validator import validate_spec
from openapi_spec_validator.readers import read_from_filename

global_had_load_error = False

def validate_openapi(filepath):
    spec_dict, spec_url = read_from_filename(filepath)
    validate_spec(spec_dict)


def yaml_loader(filepath):
    global global_had_load_error
    with open(filepath,'r')as file_descriptor:
        try: 
            data = yaml.safe_load(file_descriptor)
        except yaml.YAMLError as err:
            global_had_load_error = True
            print("Parse error: ", err)
            return None
    return data

def make_dict(data, ypath):
    if ypath not in data:
        data[ypath] = dict()
    elif data[ypath] is None:
        data[ypath] = dict()

class YFile:
    def __init__(self, filepath):
        self.filepath = filepath
        self.data = yaml_loader(filepath)
        if self.data == None:
            return
        make_dict(self.data, 'paths')
        make_dict(self.data, 'components')
        make_dict(self.data['components'], 'schemas')
        make_dict(self.data['components'], 'parameters')
        make_dict(self.data['components'], 'responses')


def get_dict(yfile, ypath):
    tpath = yfile.data
    for i in range(len(ypath)):
        tpath = tpath[ypath[i]]
    return tpath

def check_duplicates(target, source, ypath):
    tdict = get_dict(target, ypath)
    sdict = get_dict(source, ypath)
    for k in sdict.keys():
        if (k in tdict):
            raise ValueError(f'Duplicate key "{k}" found in "{source.filepath}"')

# Both inputs are YFile objects
def merge(target, source, ypath):
    check_duplicates(target, source, ypath)
    tdict = get_dict(target, ypath)
    sdict = get_dict(source, ypath)
    tdict.update(sdict)


def main():
    parser = argparse.ArgumentParser(description='Merge parts into an openapi document')
    parser.add_argument('--main', help='Main openapi YAML document containing the outline of the desired result')
    parser.add_argument('--apidir', help='Relative path to the directory of YAML documents to merge')
    # For now, we just grab everything in apidir subdirectories plus main
    parser.add_argument('--outdir', help='Output directory to write the resulting YAML document to')
    argdict = vars(parser.parse_args())

    # Pull in the main file
    mainy = YFile(argdict['main'])

    # Pull in the parts files
    apidir = argdict['apidir']
    yaml_list = glob(apidir + "/*/*.yaml", recursive = False)
    party = list(map(YFile, yaml_list))

    # Bail if we did not get a clean load
    if global_had_load_error:
        print("Exiting due to load error")
        return 1

    # merge each section
    for p in party:
        merge(mainy, p, ['paths'])
        merge(mainy, p, ['components', 'schemas'])
        merge(mainy, p, ['components', 'parameters'])
        merge(mainy, p, ['components', 'responses'])

    outfilepath = argdict['outdir'] + '/openapi.yaml'
    outfile = open(outfilepath, 'w')
    outfile.write(yaml.safe_dump(mainy.data))
    outfile.close()
    validate_openapi(outfilepath)
    return 0

if __name__ == '__main__':
    sys.exit(main())
