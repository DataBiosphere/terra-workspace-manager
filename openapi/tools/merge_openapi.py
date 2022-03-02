"""Merge OpenAPI YAML files together into a single output file"""
import sys
import argparse
import yaml
from glob import glob
from openapi_spec_validator import validate_spec
from openapi_spec_validator.readers import read_from_filename
from pathlib import Path


class OpenApiTree:
    """Hold the association of the source file and the loaded yaml"""

    def __init__(self, filepath):
        """Load yaml from the filepath. Make sure all of the dict's are initialized.

         Note that a load failure does not cause an exit. We want to get all of the load errors
         in one pass and then exit.
         """
        self.filepath = filepath
        self.load_yaml()
        if self.load_failed():
            return
        self.ensure_dict_exists(self.data, 'paths')
        self.ensure_dict_exists(self.data, 'components')
        self.ensure_dict_exists(self.data['components'], 'schemas')
        self.ensure_dict_exists(self.data['components'], 'parameters')
        self.ensure_dict_exists(self.data['components'], 'responses')

    def ensure_dict_exists(self, input_dict, yaml_path):
        """Fill in a dictionary if none is present"""
        if yaml_path not in input_dict:
            input_dict[yaml_path] = dict()
        if input_dict[yaml_path] is None:
            input_dict[yaml_path] = dict()

    def load_failed(self):
        """Expose the state of the load"""
        return self.data == None

    def load_yaml(self):
        """Load a yaml file and handle parse errors"""
        with open(self.filepath,'r') as file_descriptor:
            try:
                self.data = yaml.safe_load(file_descriptor)
            except yaml.YAMLError as err:
                print("Parse error: ", err)
                self.data = None

def check_duplicates(target, source, yaml_path):
    """Check for duplicate keys in a merge target and the source"""
    tdict = lookup_dict(target, yaml_path)
    sdict = lookup_dict(source, yaml_path)
    dups = set.intersection(set(tdict.keys()), set(sdict.keys()))

    if 0 != len(dups):
        raise ValueError(f'Duplicate key(s) "{dups}" found in "{source.filepath}"')

def lookup_dict(OpenApiTree, yaml_path):
    """Given an array of keys, traverse dictionaries, returning the last one"""
    result_dict = OpenApiTree.data
    for p in enumerate(yaml_path):
        # Enumerate returns a list where the second member is the string, so we have to get index 1
        result_dict = result_dict[p[1]]
    return result_dict


def merge(target, source, yaml_path):
    """Merge the source dictionary at yaml_path into the target dictionary at yaml_path"""
    check_duplicates(target, source, yaml_path)
    target_dict = lookup_dict(target, yaml_path)
    source_dict = lookup_dict(source, yaml_path)
    target_dict.update(source_dict)

def order_merge(input_dict):
    """Generate a result dictionary in the desired order.

    We generate the top-level items in the traditional order:
      openapi
      info
      paths
      components
      security

    We generate the paths, components, and component sections in alphabetical order. By
    happy coincidence, that leaves the components.securitySchemes last, right next to
    the security element that references them.
    """
    for k in input_dict['components'].keys():
        input_dict['components'][k] = sortDictionary(input_dict['components'][k])
    components = sortDictionary(input_dict['components'])
    result = dict()
    result['openapi'] = input_dict['openapi']
    result['info'] = input_dict['info']
    result['paths'] = sortDictionary(input_dict['paths'])
    result['components'] = components
    result['security'] = input_dict['security']
    return result

def sortDictionary(input_dict):
    return dict(sorted(input_dict.items(), key=lambda t: t[0]))

def validate_openapi(filepath):
    """Run the OpenApi validator on the combined output"""
    spec_dict = read_from_filename(filepath)[0]
    validate_spec(spec_dict)

def main():
    parser = argparse.ArgumentParser(description='Merge parts into an openapi document')
    parser.add_argument('--main', help='Main openapi YAML document containing the outline of the desired result')
    parser.add_argument('--apidir', help='Relative path to the directory of YAML documents to merge')
    parser.add_argument('--outdir', help='Output directory to write the resulting YAML document to')
    argdict = vars(parser.parse_args())

    # Check the outdir early
    outdir = argdict['outdir']
    Path(outdir).mkdir(parents=True, exist_ok=True)

    # Pull in the main file
    main_tree = OpenApiTree(argdict['main'])

    # Pull in the parts files
    apidir = argdict['apidir']
    yaml_list = glob(apidir + "/*/*.yaml", recursive = False)
    part_tree_list = list(map(OpenApiTree, yaml_list))

    # Bail if we did not get a clean load
    for tree in [main_tree] + part_tree_list:
        if tree.load_failed():
            print
            return 1

    # Merge each section. We don't worry about the order of the merges or the order of
    # the files we loaded. It is ordered below.
    for p in part_tree_list:
        merge(main_tree, p, ['paths'])
        merge(main_tree, p, ['components', 'schemas'])
        merge(main_tree, p, ['components', 'parameters'])
        merge(main_tree, p, ['components', 'responses'])

    # Put the merged parts into a pretty order
    result = order_merge(main_tree.data)

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
