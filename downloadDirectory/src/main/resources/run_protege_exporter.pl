#!/usr/bin/perl

use GKB::WebUtils;
use strict;
use warnings;

my $wu = GKB::WebUtils->new_from_cgi();
my $id = $ARGV[0] || die "No ID specified specified.\n";
my $file_name = $ARGV[1] || die "No filename specified specified.\n";

$wu->create_protege_project_wo_orthologues($file_name,[$id]);