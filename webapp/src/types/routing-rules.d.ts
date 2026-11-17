export interface RoutingRulesData {
    name: string;
    description: string;
    priority: number;
    actions: string[];
    condition: string;
}

export interface RoutingRuleStatus extends RoutingRulesData {
    active: boolean;
    error: string | null;
}
