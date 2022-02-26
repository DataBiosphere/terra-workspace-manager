import sys
import argparse
import yaml
from yaml import SafeLoader, SafeDumper
from glob import glob
from openapi_spec_validator import validate_spec
from openapi_spec_validator.readers import read_from_filename
from pathlib import Path

global_had_load_error = False

def validate_openapi(filepath):
    spec_dict = read_from_filename(filepath)[0]
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
    """ Hold the association of the source file and the loaded yaml """
    def __init__(self, filepath):
        """
         Make sure all of the dict's are initialized.
         Note that a load failure does not cause an exit. We want to get all of the load errors
         in one pass and then exit. The global `global_had_load_error` is used to track the state.
         """
        self.filepath = filepath
        self.data = yaml_loader(filepath)
        if self.data == None:
            return
        make_dict(self.data, 'paths')
        make_dict(self.data, 'components')
        make_dict(self.data['components'], 'schemas')
        make_dict(self.data['components'], 'parameters')
        make_dict(self.data['components'], 'responses')

def lookup_dict(yfile, ypath):
    tpath = yfile.data
    for i in range(len(ypath)):
        tpath = tpath[ypath[i]]
    return tpath

def check_duplicates(target, source, ypath):
    tdict = lookup_dict(target, ypath)
    sdict = lookup_dict(source, ypath)
    for k in sdict.keys():
        if (k in tdict):
            raise ValueError(f'Duplicate key "{k}" found in "{source.filepath}"')

# Both inputs are YFile objects
def merge(target, source, ypath):
    check_duplicates(target, source, ypath)
    tdict = lookup_dict(target, ypath)
    sdict = lookup_dict(source, ypath)
    tdict.update(sdict)

def sortDictionary(indict):
    return dict(sorted(indict.items(), key=lambda t: t[0]))

# Generate a result dictionary in the desired order.
#
# We generate the top-level items in the traditional order:
#  openapi
#  info
#  paths
#  components
#  security
#
# We generate the paths, components, and component sections in alphabetical order. By
# happy coincidence, that leaves the securitySchemes last, right next to the security
# element that references them.
def order_merge(indict):
    paths = sortDictionary(indict['paths'])
    for k in indict['components'].keys():
        indict['components'][k] = sortDictionary(indict['components'][k])
    components = sortDictionary(indict['components'])
    result = dict()
    result['openapi'] = indict['openapi']
    result['info'] = indict['info']
    result['paths'] = paths
    result['components'] = components
    result['security'] = indict['security']
    return result

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

    # Merge each section
    for p in party:
        merge(mainy, p, ['paths'])
        merge(mainy, p, ['components', 'schemas'])
        merge(mainy, p, ['components', 'parameters'])
        merge(mainy, p, ['components', 'responses'])

    # Put the merged parts into a pretty order
    result = order_merge(mainy.data)

    # Write the output
    outdir = argdict['outdir']
    Path(outdir).mkdir(parents=True, exist_ok=True)

    outfilepath = outdir + '/openapi.yaml'
    outfile = open(outfilepath, 'w')
    outfile.write(yaml.safe_dump(result, sort_keys=False))
    outfile.close()

    # Validate the resulting openapi file
    validate_openapi(outfilepath)
    return 0

if __name__ == '__main__':
    sys.exit(main())
