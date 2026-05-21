import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import YAML from "yaml";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";

const root = process.cwd();
const openApiDir = path.join(root, "libs/contracts/openapi");
const eventDir = path.join(root, "libs/contracts/events");

function readDirectoryFiles(directory, extension) {
  if (!fs.existsSync(directory)) {
    throw new Error(`Missing required contract directory: ${directory}`);
  }

  return fs
    .readdirSync(directory)
    .filter((file) => file.endsWith(extension))
    .map((file) => path.join(directory, file))
    .sort();
}

function validateOpenApi(file) {
  const document = YAML.parse(fs.readFileSync(file, "utf8"));
  const required = ["openapi", "info", "paths"];
  const missing = required.filter((field) => document[field] === undefined);

  if (missing.length > 0) {
    throw new Error(`${file} is missing OpenAPI fields: ${missing.join(", ")}`);
  }

  if (!String(document.openapi).startsWith("3.")) {
    throw new Error(`${file} must use OpenAPI 3.x`);
  }

  if (Object.keys(document.paths).length === 0) {
    throw new Error(`${file} must declare at least one path`);
  }
}

function validateEventSchema(file, ajv) {
  const schema = JSON.parse(fs.readFileSync(file, "utf8"));

  if (!schema.$id || !schema.title || schema.type !== "object") {
    throw new Error(`${file} must define $id, title, and object type`);
  }

  ajv.compile(schema);
}

const openApiFiles = readDirectoryFiles(openApiDir, ".yaml");
const eventFiles = readDirectoryFiles(eventDir, ".schema.json");

const ajv = new Ajv2020({ strict: true, allErrors: true });
addFormats(ajv);

for (const file of openApiFiles) {
  validateOpenApi(file);
}

for (const file of eventFiles) {
  validateEventSchema(file, ajv);
}

console.log(`Validated ${openApiFiles.length} OpenAPI specs and ${eventFiles.length} event schemas.`);
