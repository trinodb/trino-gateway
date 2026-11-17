/**
 * @vitest-environment jsdom
 */
import type * as React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RoutingRules } from "./routing-rules";
import { Role, useAccessStore } from "../store";
import { RoutingRuleStatus } from "../types/routing-rules";

const apiMocks = vi.hoisted(() => ({
  routingRulesApi: vi.fn(),
  createRoutingRuleApi: vi.fn(),
  deleteRoutingRuleApi: vi.fn(),
  updateRoutingRulesApi: vi.fn(),
}));

vi.mock("../api/webapp/routing-rules.ts", () => apiMocks);

vi.mock("@douyinfe/semi-ui", async () => {
  const ReactModule = await import("react");
  const FormContext = ReactModule.createContext<{
    values: Record<string, unknown>;
    requiredFields: Set<string>;
  } | null>(null);

  const Toast = {
    error: vi.fn(),
    success: vi.fn(),
  };

  const Button = ({ children, onClick }: { children: React.ReactNode; onClick?: () => void }) => (
    <button type="button" onClick={onClick}>
      {children}
    </button>
  );

  const Banner = ({ description, type }: { description: string; type: string }) => (
    <div role="alert" data-type={type}>
      {description}
    </div>
  );

  const Card = ({
    children,
    title,
    headerExtraContent,
    footer,
  }: {
    children: React.ReactNode;
    title?: React.ReactNode;
    headerExtraContent?: React.ReactNode;
    footer?: React.ReactNode;
  }) => (
    <section>
      <header>
        {title}
        {headerExtraContent}
      </header>
      {children}
      <footer>{footer}</footer>
    </section>
  );

  const Field = ({
    field,
    label,
    initValue = "",
    rules = [],
    type = "text",
  }: {
    field: string;
    label: string;
    initValue?: string | number;
    rules?: { required?: boolean }[];
    type?: string;
  }) => {
    const context = ReactModule.useContext(FormContext);
    const [value, setValue] = ReactModule.useState(String(initValue));

    ReactModule.useEffect(() => {
      if (!context) {
        return;
      }
      context.values[field] = value;
      if (rules.some(rule => rule.required)) {
        context.requiredFields.add(field);
      }
    }, [context, field, rules, value]);

    return (
      <label>
        {label}
        {type === "textarea" ? (
          <textarea aria-label={label} value={value} onChange={event => setValue(event.target.value)} />
        ) : (
          <input aria-label={label} type={type} value={value} onChange={event => setValue(event.target.value)} />
        )}
      </label>
    );
  };

  const FormComponent = ({
    children,
    getFormApi,
  }: {
    children: React.ReactNode;
    getFormApi?: (api: { validate: () => Promise<Record<string, unknown>> }) => void;
  }) => {
    const control = ReactModule.useRef<{ values: Record<string, unknown>; requiredFields: Set<string> }>({
      values: {},
      requiredFields: new Set<string>(),
    }).current;
    const getFormApiRef = ReactModule.useRef(getFormApi);
    const api = ReactModule.useMemo(
      () => ({
        validate: async () => {
          for (const field of control.requiredFields) {
            const value = control.values[field];
            if (value == null || String(value).trim() === "") {
              throw new Error("validation failed");
            }
          }
          return { ...control.values };
        },
      }),
      [control]
    );

    ReactModule.useEffect(() => {
      getFormApiRef.current?.(api);
    }, [api]);

    return <FormContext.Provider value={control}>{children}</FormContext.Provider>;
  };

  const Form = Object.assign(FormComponent, {
    Input: (props: React.ComponentProps<typeof Field>) => <Field {...props} />,
    InputNumber: (props: React.ComponentProps<typeof Field>) => <Field {...props} type="number" />,
    TextArea: (props: React.ComponentProps<typeof Field>) => <Field {...props} type="textarea" />,
  });

  const Modal = ({
    children,
    onCancel,
    onOk,
    title,
    visible,
  }: {
    children: React.ReactNode;
    onCancel?: () => void;
    onOk?: () => void;
    title: string;
    visible: boolean;
  }) => {
    if (!visible) {
      return null;
    }
    return (
      <div role="dialog" aria-label={title}>
        <h2>{title}</h2>
        {children}
        <button type="button" onClick={onOk}>
          OK
        </button>
        <button type="button" onClick={onCancel}>
          Cancel
        </button>
      </div>
    );
  };

  const Popconfirm = ({
    children,
    content,
    onConfirm,
  }: {
    children: React.ReactNode;
    content: string;
    onConfirm?: () => void;
  }) => (
    <span>
      {children}
      <button type="button" onClick={onConfirm}>
        Confirm {content}
      </button>
    </span>
  );

  return {
    Banner,
    Button,
    Card,
    Form,
    Modal,
    Popconfirm,
    Space: ({ children }: { children: React.ReactNode }) => <>{children}</>,
    Spin: () => <span>Loading</span>,
    Tag: ({ children }: { children: React.ReactNode }) => <span>{children}</span>,
    Toast,
  };
});

const activeRule = (overrides: Partial<RoutingRuleStatus> = {}): RoutingRuleStatus => ({
  name: "rule-one",
  description: "test rule",
  priority: 10,
  condition: "true",
  actions: ["result.put(\"routingGroup\", \"default\")"],
  active: true,
  error: null,
  ...overrides,
});

describe("RoutingRules", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAccessStore.setState({
      roles: [Role.ADMIN],
      permissions: [],
      token: "token",
    });
    apiMocks.routingRulesApi.mockResolvedValue([]);
    apiMocks.createRoutingRuleApi.mockResolvedValue([]);
    apiMocks.deleteRoutingRuleApi.mockResolvedValue([]);
    apiMocks.updateRoutingRulesApi.mockResolvedValue([]);
  });

  it("shows inactive and runtime-error rule status tags", async () => {
    apiMocks.routingRulesApi.mockResolvedValue([
      activeRule({ name: "bad-rule", active: false, error: "Invalid condition" }),
      activeRule({ name: "throws-rule", error: "Fails for some requests: BLOCKED" }),
    ]);

    render(<RoutingRules />);

    expect(await screen.findByText("Not applied")).toBeTruthy();
    expect(screen.getByText("Errors")).toBeTruthy();
    expect(screen.getByText("Invalid condition")).toBeTruthy();
    expect(screen.getByText("Fails for some requests: BLOCKED")).toBeTruthy();
  });

  it("creates a routing rule and splits multi-line actions", async () => {
    const user = userEvent.setup();
    render(<RoutingRules />);

    await user.click(await screen.findByRole("button", { name: "Add rule" }));
    await user.type(screen.getByLabelText("Name"), "ui-test-create");
    await user.type(screen.getByLabelText("Description"), "created from UI");
    await user.clear(screen.getByLabelText("Priority"));
    await user.type(screen.getByLabelText("Priority"), "99");
    await user.type(screen.getByLabelText("Condition"), "true");
    await user.type(
      screen.getByLabelText("Actions"),
      "result.put(\"routingGroup\", \"etl\")\nstate.put(\"matched\", true)"
    );
    await user.click(screen.getByRole("button", { name: "OK" }));

    await waitFor(() => expect(apiMocks.createRoutingRuleApi).toHaveBeenCalledTimes(1));
    expect(apiMocks.createRoutingRuleApi).toHaveBeenCalledWith({
      name: "ui-test-create",
      description: "created from UI",
      priority: 99,
      condition: "true",
      actions: ["result.put(\"routingGroup\", \"etl\")", "state.put(\"matched\", true)"],
    });
  });

  it("keeps the add modal open when validation fails", async () => {
    const user = userEvent.setup();
    render(<RoutingRules />);

    await user.click(await screen.findByRole("button", { name: "Add rule" }));
    await user.type(screen.getByLabelText("Name"), "missing-required-fields");
    await user.click(screen.getByRole("button", { name: "OK" }));

    expect(apiMocks.createRoutingRuleApi).not.toHaveBeenCalled();
    expect(screen.getByRole("dialog", { name: "Add rule" })).toBeTruthy();
  });

  it("deletes a routing rule after confirmation", async () => {
    const user = userEvent.setup();
    apiMocks.routingRulesApi.mockResolvedValue([activeRule({ name: "delete-me" })]);

    render(<RoutingRules />);

    await user.click(await screen.findByRole("button", { name: "Confirm delete-me" }));

    await waitFor(() => expect(apiMocks.deleteRoutingRuleApi).toHaveBeenCalledWith("delete-me"));
  });

  it("edits a routing rule, keeps multi-line actions, and reloads the list", async () => {
    const user = userEvent.setup();
    apiMocks.routingRulesApi.mockResolvedValue([
      activeRule({ name: "edit-me", actions: ["result.put(\"routingGroup\", \"etl\")", "state.put(\"matched\", true)"] }),
    ]);
    render(<RoutingRules />);

    await user.click(await screen.findByRole("button", { name: "Edit" }));
    await user.clear(screen.getByLabelText("Priority"));
    await user.type(screen.getByLabelText("Priority"), "5");
    await user.type(screen.getByLabelText("Condition"), " ");
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(apiMocks.updateRoutingRulesApi).toHaveBeenCalledTimes(1));
    expect(apiMocks.updateRoutingRulesApi).toHaveBeenCalledWith({
      name: "edit-me",
      description: "test rule",
      priority: 5,
      condition: "true",
      actions: ["result.put(\"routingGroup\", \"etl\")", "state.put(\"matched\", true)"],
    });
    expect(apiMocks.routingRulesApi).toHaveBeenCalledTimes(2);
  });
});
