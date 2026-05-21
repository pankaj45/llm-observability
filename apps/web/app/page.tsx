import { Badge, Box, Group, Stack, Text, Title } from "@mantine/core";

const services = [
  { name: "Inference Gateway", port: 8080, role: "Streaming inference API" },
  { name: "Analytics Query", port: 8081, role: "Dashboard query API" },
  { name: "Ingestion Worker", port: 8082, role: "Event ingestion and persistence" }
];

export default function HomePage() {
  return (
    <Box component="main" px="xl" py="lg">
      <Stack gap="lg">
        <Group justify="space-between" align="center">
          <div>
            <Title order={1}>LLM Observability</Title>
            <Text c="dimmed">Phase 1 platform bootstrap workspace</Text>
          </div>
          <Badge variant="filled" color="teal">Bootstrap</Badge>
        </Group>

        <Box
          component="table"
          style={{
            borderCollapse: "collapse",
            border: "1px solid var(--mantine-color-gray-3)",
            width: "100%"
          }}
        >
          <thead>
            <tr>
              <Box component="th" p="sm" ta="left">Service</Box>
              <Box component="th" p="sm" ta="left">Port</Box>
              <Box component="th" p="sm" ta="left">Responsibility</Box>
            </tr>
          </thead>
          <tbody>
            {services.map((service) => (
              <tr key={service.name}>
                <Box component="td" p="sm">{service.name}</Box>
                <Box component="td" p="sm">{service.port}</Box>
                <Box component="td" p="sm">{service.role}</Box>
              </tr>
            ))}
          </tbody>
        </Box>
      </Stack>
    </Box>
  );
}
